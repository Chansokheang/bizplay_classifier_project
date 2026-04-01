import { cn } from '../../lib/utils'

export function Table({ className, ...props }) {
  return (
    <div className="relative w-full overflow-x-auto" data-slot="table-container">
      <table
        className={cn('w-full caption-bottom text-sm', className)}
        data-slot="table"
        {...props}
      />
    </div>
  )
}

export function TableHeader({ className, ...props }) {
  return (
    <thead
      className={cn('[&_tr]:border-b', className)}
      data-slot="table-header"
      {...props}
    />
  )
}

export function TableBody({ className, ...props }) {
  return (
    <tbody
      className={cn('[&_tr:last-child]:border-0', className)}
      data-slot="table-body"
      {...props}
    />
  )
}

export function TableFooter({ className, ...props }) {
  return (
    <tfoot
      className={cn('border-t font-medium [&>tr]:last:border-b-0', className)}
      data-slot="table-footer"
      {...props}
    />
  )
}

export function TableRow({ className, ...props }) {
  return (
    <tr
      className={cn('border-b transition-colors', className)}
      data-slot="table-row"
      {...props}
    />
  )
}

export function TableHead({ className, ...props }) {
  return (
    <th
      className={cn(
        'h-10 whitespace-nowrap px-4 text-left align-middle font-semibold text-xs uppercase tracking-wider',
        className,
      )}
      data-slot="table-head"
      {...props}
    />
  )
}

export function TableCell({ className, ...props }) {
  return (
    <td
      className={cn('whitespace-nowrap px-4 py-3 align-middle text-sm', className)}
      data-slot="table-cell"
      {...props}
    />
  )
}

export function TableCaption({ className, ...props }) {
  return (
    <caption
      className={cn('mt-4 text-sm', className)}
      data-slot="table-caption"
      {...props}
    />
  )
}
